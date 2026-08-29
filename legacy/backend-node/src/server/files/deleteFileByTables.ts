import { PoolConnection } from "mysql2/promise";
import setQuery from "../../helper/setQuery";

export const deleteFileByTables = async (file_id : string, table_id : string, query : PoolConnection) => {
    const sql = `
        UPDATE Table_Files
            SET table_file_type = 'Deleted'
            WHERE file_id = ?
                AND table_id = ?`;

    const params = [file_id, table_id];
    const response = await setQuery(sql, params, query);

    return response;
}