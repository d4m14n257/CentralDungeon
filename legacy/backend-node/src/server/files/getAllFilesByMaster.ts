import getQuery from "../../helper/getQuery";
import { FileList } from "../../models/models";

export const getAllFilesByMaster = async (user_id : string, table_id : string) => {
    //TODO: Refactory query because of check logic in files publics and private
    const sql = `
        SELECT f.id, f.name, f.mine, f.size
            FROM Files f 
            WHERE f.user_created_id = ?
                AND f.type_file = 'Private'
                AND f.id  NOT IN (
                    SELECT DISTINCT tf.file_id 
                        FROM Table_Files tf 
                    WHERE tf.table_id = ?
                        AND tf.table_file_type != 'Deleted'
                )`;
    
    const params = [user_id, table_id];
    const response = await getQuery(sql, params)
    
    return response;
}