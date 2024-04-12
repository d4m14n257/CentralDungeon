import getQuery from "../../helper/getQuery";

export const getOwnerTables = async (utc : Promise<string>, user_id : string) => {
    const sql = `
    WITH RECURSIVE find_first AS (
        SELECT t.id as table_id, tg.id, tg.name, tg.tag_id 
            FROM Tables t 
                JOIN Masters m ON m.table_id = t.id
                LEFT JOIN Table_Tags tt ON tt.table_id = t.id
                LEFT JOIN Tags tg ON tg.id = tt.tag_id
            WHERE m.user_master_id = ?
                AND m.master_type = 'Owner'
                AND (tt.status = 'Used' OR tt.status IS NULL)
        UNION ALL
        SELECT tt.table_id, s.id, s.name, s.tag_id
            FROM Tags s
                LEFT JOIN Table_Tags tt ON tt.tag_id = s.id
                JOIN find_first f ON s.id = f.tag_id
        )
        SELECT m.table_id as id, t.name, t.description, pt.players, GROUP_CONCAT(m.name SEPARATOR ", ") as tags, CONVERT_TZ(t.startdate, t.timezone, ?) as startdate,
            t.status
            FROM find_first m
                JOIN Tables t ON t.id = m.table_id
                JOIN Players_Table pt ON pt.table_id = t.id
            WHERE tag_id is NULL
            GROUP BY m.table_id`;

        const params = [user_id, utc];
        return await getQuery(sql, params);
}