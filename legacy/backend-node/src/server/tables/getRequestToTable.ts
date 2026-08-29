import getQuery from "../../helper/getQuery";

export const getRequestToTables = async (utc : Promise<string>, user_id : string, type : string = '') : Promise<any> => {
    let sql = `
        SELECT ur.id, ut.id as table_id, ut.name, ut.masters, ur.status, CONVERT_TZ(ur.created_at, u.timezone, ?) as created_at
            FROM (
                SELECT t.id, t.name, 
                    CASE
                        WHEN GROUP_CONCAT(u.name) IS NULL THEN 'No hay masters asignados.'
                        ELSE GROUP_CONCAT(u.name) 
                    END as masters 
                    FROM Tables t 
                        LEFT JOIN Masters m ON m.table_id = t.id 
                        LEFT JOIN Users u ON m.user_master_id = u.id
                    WHERE (u.status = 'Allowed' OR u.id IS NULL)
                    GROUP BY t.id
                ) as ut
                JOIN Users_registration ur ON ur.table_id = ut.id
                JOIN Users u ON u.id = ur.user_id  
            WHERE ur.user_id = ?
                AND ur.status NOT IN ('Player', 'Blocked', 'Deleted') `;
    
    if(type == 'Rejected')
        sql += `
                AND ur.status = 'Rejected'`;
    else if(type == 'Candidate')
        sql += `
            AND ur.status = 'Candidate'`
    
    const params = [utc, user_id];     
    return await getQuery(sql, params);
}