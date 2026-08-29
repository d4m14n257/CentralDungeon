import getQuery from "../../helper/getQuery"

export const getCataloguesInTables = async (table_name : string, table_id : string) => {
    let sql = `
        SELECT c.id, c.name, c.status 
            FROM ${table_name} c `;

    if(table_name == 'Tags')
        sql += `LEFT JOIN Table_Tags tc ON tc.tag_id = c.id `;
    else if(table_name == 'Systems')
        sql += `LEFT JOIN Table_Systems tc ON tc.system_id = c.id `;
    else if(table_name == 'Platforms')
        sql += `LEFT JOIN Table_Platforms tc ON tc.platform_id = c.id `;

    sql += `
        WHERE tc.table_id = ?
            AND tc.status = 'Used'
            AND c.status != 'Deleted'`;

    const params = [table_id];
    const response = await getQuery(sql, params);

    return response;
}