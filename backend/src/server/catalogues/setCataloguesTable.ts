import { PoolConnection } from "mysql2/promise";
import setQuery from "../../helper/setQuery";

export async function setCatalogueTables(table_id : string, catalogue_id : string, table_name: string, query : PoolConnection) : Promise<any> {
    let sql = `
        INSERT INTO Table_${table_name} (table_id, `;

    switch (table_name) {
        case 'Tags': {
            sql += `tag_id) `;
            break;
        }
            
        case 'Systems': {
            sql += `system_id) `;
            break;
        }
            
        case 'Platforms': {
            sql += `platform_id) `;
            break;
        }
    } 

    sql += `VALUES (?, ?)`;

    const params = [table_id, catalogue_id];
    const response = await setQuery(sql, params, query);

    if(response.errno == 1062) {
        let sql_update = `
            UPDATE Table_${table_name}
                SET status = 'Used'
                WHERE table_id = ?
                    AND `;
        
        switch(table_name) {
            case 'Tags': {
                sql_update += `tag_id = ? `;
                break;
            }
            case 'Systems': {
                sql_update += `system_id = ? `;
                break;
            }
            case 'Platforms': {
                sql_update += `platform_id = ? `;
                break;
            }
        }

        const updated = await setQuery(sql_update, params, query);
        return await updated;
    }
    else
        return await response;
}