import { PoolConnection } from "mysql2/promise";
import setQuery from "../../helper/setQuery";

export const deleteTables = async (table_id : string, query : PoolConnection) => {
    const sql = `
        UPDATE Tables
            SET status = 'Deleted'
            WHERE id = ?`;

    const params = [table_id];
    const response = await setQuery(sql, params, query);

    return await {...response}   
}