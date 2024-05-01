import { PoolConnection } from "mysql2/promise";
import setQuery from "../../helper/setQuery";

export const deleteCataloguesByTables = async (table_name : string, table_id : string, query : PoolConnection) => {
    const sql = `
        UPDATE ${table_name}
            SET status = 'Deleted'
            WHERE table_id = ?`;

    const params = [table_id];
    const response = await setQuery(sql, params, query);

    return await {...response}   
}