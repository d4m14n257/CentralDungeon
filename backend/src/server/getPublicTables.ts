import getQuery from "../helper/getQuery";

export const getPublicTable = async (utc : Promise<string>, user_id : string) : Promise<any> => {
    const sql = `
    SELECT pt.*
        FROM (
            SELECT t.id, t.name, t.description, CONVERT_TZ(t.startdate, t.timezone, ?) as startdate, GROUP_CONCAT(u.name) as master
                FROM Tables t 
                    LEFT JOIN Masters m ON m.table_id = t.id 
                    LEFT JOIN Users u ON m.user_master_id = u.id
                WHERE t.status = 'Opened'
                    AND t.table_type = 'Public'
                    AND (u.status = 'Allowed' OR u.status IS NULL)
                    AND (m.status = 'Created' OR m.status IS NULL)
                    AND (m.user_master_id != ? OR m.user_master_id IS NULL)
                GROUP BY t.id 
                ORDER BY t.created_at
            ) as pt
            LEFT JOIN Users_registration ur ON ur.table_id = pt.id
                AND ur.user_id = ? OR ur.user_id IS NULL
        WHERE ur.id IS NULL`;

    const params = [utc, user_id, user_id];
    return await getQuery(sql, params);
}