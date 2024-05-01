import getQuery from "../../helper/getQuery";

export const getScheduleInTables = async (table_id : string) => {
    const sql = `
        SELECT d.id, d.weekday, d.hourtime 
            FROM Days d 
            WHERE d.table_id = ?
                AND d.status = 'Created'`;

    const params = [table_id];
    const response = await getQuery(sql, params);

    return response;
}