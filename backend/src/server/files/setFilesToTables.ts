import { PoolConnection } from "mysql2/promise";
import setQuery from "../../helper/setQuery";

export const setFilesToTables = async (file_id : string, table_id : string, query : PoolConnection) => {
    const sql = `
        INSERT INTO Table_Files (table_id, file_id, table_file_type)
            VALUE (?, ?, 'Preparation')`;

    const params = [table_id, file_id];
    const response = await setQuery(sql, params, query);

    return response;
}