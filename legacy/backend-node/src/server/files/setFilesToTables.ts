import { PoolConnection } from "mysql2/promise";
import setQuery from "../../helper/setQuery";

//TODO: Add status to use in all table status.

export const setFilesToTables = async (file_id : string, table_id : string, query : PoolConnection) => {
    const sql = `
        INSERT INTO Table_Files (table_id, file_id, table_file_type)
            VALUE (?, ?, 'Preparation')`;

    const params = [table_id, file_id];
    const response = await setQuery(sql, params, query);

    if(response.errno == 1062) {
        const sql_update = `
            UPDATE Table_Files
                SET table_file_type = 'Preparation'
            WHERE table_id = ?
                AND file_id = ?`;

        const updated = await setQuery(sql_update, params, query);
        return updated
    }
    else
        return response;
}