import { PoolConnection } from "mysql2/promise";
import setQuery from "../../helper/setQuery";

export async function setCatalogueTables(table_id : string, catalogue_id : string, table_name: string, query : PoolConnection) : Promise<any> {
    let sql = `
        INSERT INTO ${table_name} (table_id, `;

    if(table_name == 'Table_Tags')
        sql += `tag_id) `;
    else if(table_name == 'Table_Systems')
        sql += `system_id) `;
    else if(table_name == 'Table_Platforms')
        sql += `platform_id) `;

    sql += `VALUES (?, ?)`;

    const params = [table_id, catalogue_id];
    const response = await setQuery(sql, params, query);
    
    return await response;
}