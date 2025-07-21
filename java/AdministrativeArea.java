import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.json.JSONUtil;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

public class AreaParser {

    // 定义四个直辖市的代码前缀（前2位），用于特殊判断
    private static final Set<String> MUNICIPALITY_PREFIXES = new HashSet<>();

    static {
        MUNICIPALITY_PREFIXES.add("11"); // 北京
        MUNICIPALITY_PREFIXES.add("12"); // 天津
        MUNICIPALITY_PREFIXES.add("31"); // 上海
        MUNICIPALITY_PREFIXES.add("50"); // 重庆
    }

    /**
     * 解析txt文件，处理直辖市特殊结构
     */
    public static List<AdministrativeArea> parse(String filePath) throws IOException {
        List<AdministrativeArea> allProvinces = new ArrayList<>();
        AdministrativeArea currentProvince = null; // 当前省级（可能是普通省份或直辖市）
        AdministrativeArea currentCity = null; // 当前地级（仅普通省份有）
        List<String> lines = FileUtil.readLines(filePath, Charset.defaultCharset());
        for (String line : lines) {
            if (line.isEmpty()) {
                continue;
            }


            // 分割名称和代码（格式："名称 代码"）
            String[] parts = line.split("\\s+");
            if (parts.length != 2) {
                continue;
            }
            ;
            String name = parts[0].trim();
            String code = parts[1].trim();
            if(NumberUtil.isNumber(name)){
                name = parts[1].trim();
                code = parts[0].trim();
            }

            // 1. 处理省级行政区（含直辖市）
            if (code.endsWith("0000")) {
                AdministrativeArea province = new AdministrativeArea(name, code);
                allProvinces.add(province);
                currentProvince = province;
                currentCity = null; // 重置地级
            }
            // 2. 处理地级行政区（仅普通省份有，直辖市无此层级）
            else if (code.endsWith("00")) {
                // 若当前省级是直辖市，则跳过（直辖市无地级）
                if (currentProvince == null || isMunicipality(currentProvince.getId())) {
                    continue;
                }

                // 普通省份的地级：前2位与省级一致
                if (code.substring(0, 2).equals(currentProvince.getId().substring(0, 2))) {
                    AdministrativeArea city = new AdministrativeArea(name, code);
                    currentProvince.getChildren().add(city);
                    currentCity = city;
                } else {
                    System.out.println(line);
                }
            }
            // 3. 处理县级行政区（含直辖市的区/县）
            else {
                if (currentProvince == null) {
                    continue;
                }
                ;

                // 3.1 若当前是直辖市：直接将区/县作为省级的子节点
                if (isMunicipality(currentProvince.getId())) {
                    // 直辖市的区/县代码前4位应为"XX01"（如北京1101xx，上海3101xx）
                    if (code.substring(0, 4).equals(currentProvince.getId().substring(0, 2) + "01")) {
                        AdministrativeArea county = new AdministrativeArea(name, code);
                        currentProvince.getChildren().add(county);
                    } else {
                        // 长三角一体化示范区 310052 也是上海的
                        if ("310052".equals(code)) {
                            AdministrativeArea county = new AdministrativeArea(name, code);
                            currentProvince.getChildren().add(county);
                        } else {
                            // 一般是省级行政区 直辖管理的
                            AdministrativeArea county = new AdministrativeArea(name, code);
                            currentProvince.getChildren().add(county);
                        }
                    }
                }
                // 3.2 若当前是普通省份：县级属于地级的子节点
                else if (currentCity != null) {

                    // 普通省份的县级：前4位与地级一致
                    if (code.substring(0, 4).equals(currentCity.getId().substring(0, 4))) {
                        AdministrativeArea county = new AdministrativeArea(name, code);
                        currentCity.getChildren().add(county);
                    } else {
                        // 说明是省的直辖区 直辖县
                        AdministrativeArea city = new AdministrativeArea(name, code);
                        currentProvince.getChildren().add(city);
                    }
                } else {
                    System.out.println(line);
                }
            }
        }
        return allProvinces;
    }

    /**
     * 判断是否为直辖市（根据省级代码前2位）
     */
    private static boolean isMunicipality(String provinceCode) {
        return provinceCode.length() >= 2 && MUNICIPALITY_PREFIXES.contains(provinceCode.substring(0, 2));
    }

    // 测试方法
    public static void main(String[] args) {
        try {
            List<AdministrativeArea> provinces = parse("origin/2023年中华人民共和国县以上行政区划代码.txt");
            System.out.println("总省级行政区数量：" + provinces.size());

            Map<String, AdministrativeArea> areaMap = new HashMap<>();
            // 递归 把所有的数据放到map中
            AdministrativeArea china = new AdministrativeArea("", "0");
            putToMap(areaMap, provinces, china);
            System.out.println("总数据量：" + areaMap.size());
            // toJson
            System.out.println(JSONUtil.toJsonPrettyStr(provinces));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void putToMap(Map<String, AdministrativeArea> areaMap, List<AdministrativeArea> list, AdministrativeArea parent) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        for (AdministrativeArea item : list) {
            // 设置额外的属性
            // parentId
            String parentId = parent.getId();

            // parentIds
            String parentParentIds = parent.getParentIds() == null ? "" : parent.getParentIds();
            String parentIds = parentParentIds + parentId + ",";

            // parentNames
            String parentParentNames = parent.getParentNames() == null ? "" : parent.getParentNames();
            String parentNames = null;
            if (!parentParentNames.isEmpty()) {
                parentNames = parentParentNames + " " + parent.getName();
            } else {
                parentNames = parent.getName();
            }
            // fullName
            String fullName = null;
            if (!parentNames.isEmpty()) {
                fullName = parentNames + " " + item.getName();
            } else {
                fullName = item.getName();
            }
            item.setParentId(parentId);
            item.setParentIds(parentIds);
            item.setParentNames(parentNames.trim());
            item.setFullName(fullName.trim());
            if(item.getChildren().isEmpty()){
                item.setChildren(null);
            }
            areaMap.put(item.getId(), item);
            putToMap(areaMap, item.getChildren(), item);
        }
    }
}
