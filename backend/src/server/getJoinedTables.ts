import getQuery from "../helper/getQuery";

export const getJoinedTable = async (utc : Promise<string>, user_id : string) : Promise<any> => {
    const sql = `
    SELECT t.id, t.name, t.description, CONVERT_TZ(t.startdate, t.timezone, ?) as startdate, GROUP_CONCAT(um.name) as master
    FROM Users_registration ur 
        JOIN Users up ON up.id = ur.user_id
        JOIN Tables t ON t.id = ur.table_id
        LEFT JOIN Masters m ON t.id = m.table_id
        LEFT JOIN Users um ON m.user_master_id = um.id 
    WHERE up.id = ?
        AND t.status NOT IN ('Finished', 'Deleted')
        AND ur.status = 'Player'
    GROUP BY t.id`;

    const params = [utc, user_id];
    return await getQuery(sql, params);
}