import { PoolConnection } from "mysql2/promise";
import setQuery from "../../helper/setQuery";

export const deleteCataloguesByIndex = async (table_name : string, catalogue_id : string, table_id : string, query : PoolConnection) => {
    let sql = `
        UPDATE Table_${table_name}
            SET status = 'Deleted'
            WHERE table_id = ?
                AND `;

    switch (table_name) {
        case 'Tags': {
            sql += `tag_id = ?`
            break;
        }
        case 'Systems': {
            sql += `system_id = ?`
            break;
        }
        case 'Platforms': {
            sql += `platform_id = ?`
            break;
        }
    }

    const params = [table_id, catalogue_id];
    const response = await setQuery(sql, params, query);

    return await {...response}   
}