import { PoolConnection } from "mysql2/promise";
import setQuery from "../../helper/setQuery";

export const deleteFilesByTables = async (table_id : string, query : PoolConnection) => {
    const sql = `
        UPDATE Table_Files
            SET table_file_type = 'Deleted'
            WHERE table_id = ?`;

    const params = [table_id];
    const response = await setQuery(sql, params, query);

    return response;
}