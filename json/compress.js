const fs = require('fs');

// 读取 JSON 文件
const rawData = fs.readFileSync('2023/fullData.json', 'utf8');
const jsonData = JSON.parse(rawData);

// 递归删除字段
const cleanedData = removeFieldsRecursively(jsonData);

// 将结果写入新文件
fs.writeFileSync('2023/data.json', JSON.stringify(cleanedData, null, 2), 'utf8');

console.log('字段已删除，结果保存到 data.json');

function removeFieldsRecursively(data) {
    // 如果是数组，递归处理每个元素
    if (Array.isArray(data)) {
        return data.map(removeFieldsRecursively);
    }

    // 如果是对象，处理其属性
    if (typeof data === 'object' && data !== null) {
        const {  parentIds, parentNames, fullName, ...rest } = data;

        // 递归处理子级对象
        Object.keys(rest).forEach(key => {
            rest[key] = removeFieldsRecursively(rest[key]);
        });

        return rest;
    }

    // 其他类型（如字符串、数字等）直接返回
    return data;
}
