import getQuery from "../../helper/getQuery";

export const getAllFilesByMaster = async (user_id : string, table_id : string) => {
    //TODO: Refactory query because of check logic in files publics and private
    const sql = `
        SELECT f.id, f.name, f.mine, f.size
            FROM Files f 
                JOIN Table_Files tf ON tf.file_id = f.id
            WHERE tf.table_id != ?
                AND f.user_created_id = ?;`

    const params = [table_id, user_id];
    const response = await getQuery(sql, params)

    return response;
}