import getQuery from "../../helper/getQuery";

export const getRequestOnTables = async (utc: Promise<string>, user_id : string) : Promise<any> => {
    const sql = `
        SELECT ur.id, t.id as table_id, t.name, u.name as username, CONVERT_TZ(ur.created_at, u.timezone , ?) as created_at, u.karma
            FROM Tables t
                JOIN Masters m ON m.table_id = t.id 
                JOIN Users_registration ur ON ur.table_id = t.id 
                JOIN Users u ON u.id = ur.user_id 
            WHERE m.user_master_id = ?
                AND ur.status = 'Candidate'
                AND u.status = 'Allowed'`;
    
    const params = [utc, user_id];     
    return await getQuery(sql, params);
}