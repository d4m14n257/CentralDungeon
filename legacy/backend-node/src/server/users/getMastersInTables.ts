import getQuery from "../../helper/getQuery";

export const getMastersInTables = async (table_id : string) => {
    const sql = `
        SELECT u.id, u.name as username, m.master_type
            FROM Masters m
                JOIN Users u ON u.id = m.user_master_id
            WHERE m.table_id = ?
                AND m.status = 'Created'
                AND u.status = 'Allowed'`;

    const params = [table_id];
    const response = await getQuery(sql, params);

    return response;
}