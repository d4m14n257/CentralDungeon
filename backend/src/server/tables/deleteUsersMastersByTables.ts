import { PoolConnection } from "mysql2/promise";
import setQuery from "../../helper/setQuery";

export const deleteUsersMastersByTables = async (table_id : string, query : PoolConnection) => {
    const sql = `
        UPDATE Masters
            SET status = 'Deleted'
            WHERE table_id = ?`;

    const params = [table_id];
    const response = await setQuery(sql, params, query);

    return await {...response}   
}