import getQuery from "../../helper/getQuery";

export const getScheduleInTables = async (table_id : string) => {
    const sql = `
    SELECT d.weekday, d.hourtime 
        FROM Days d 
        WHERE d.table_id = ?
            AND d.status = 'Created'
        ORDER BY
            CASE d.weekday
                WHEN 'Sunday' THEN 1
                WHEN 'Monday' THEN 2
                WHEN 'Tuesday' THEN 3
                WHEN 'Wednesday' THEN 4
                WHEN 'Thursday' THEN 5
                WHEN 'Friday' THEN 6
                WHEN 'Saturday' THEN 7
            END`;

    const params = [table_id];
    const response = await getQuery(sql, params);

    return response;
}