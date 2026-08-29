import { PoolConnection } from "mysql2/promise";
import setQuery from "../../helper/setQuery";

export const updateFileStatus = async (file_id : string, status : string, query : PoolConnection) => {
    const sql = `
        UPDATE Files
            SET type_file = ?
            WHERE id = ?`;

    const params = [status, file_id];
    const response = await setQuery(sql, params, query);

    return response;
}