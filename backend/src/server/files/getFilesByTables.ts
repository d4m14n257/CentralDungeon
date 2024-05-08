import getQuery from "../../helper/getQuery";

export const getFilesByTables = async (table_id: string) => {
    const sql = `
        SELECT id, name, source, mine
            FROM Files f 
                JOIN Table_Files tf ON tf.file_id = f.id 
            WHERE tf.table_id = ?
                AND tf.table_file_type = 'Preparation'`;

    const params = [table_id];
    const response = await getQuery(sql, params);

    return response;
}