import getQuery from "../../helper/getQuery";

export const getRequestToTables = async (utc : Promise<string>, user_id : string) => {
    const sql = `
        SELECT ut.*, ur.status, CONVERT_TZ(ur.created, u.timezone, ?) as created
            FROM (
                SELECT t.id, t.name, GROUP_CONCAT(u.name) as master 
                    FROM Tables t 
                        LEFT JOIN Masters m ON m.table_id = t.id 
                        LEFT JOIN Users u ON m.user_master_id = u.id
                    WHERE (u.status = 'Allowed' OR u.id IS NULL)
                    GROUP BY t.id
                ) as ut
                JOIN Users_registration ur ON ur.table_id = ut.id
                JOIN Users u ON u.id = ur.user_id  
            WHERE ur.user_id = ?
                AND ur.status NOT IN ('Player', 'Blocked', 'Deleted')`;
    
    const params = [utc, user_id];     
    return await getQuery(sql, params);
}