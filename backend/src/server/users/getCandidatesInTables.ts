import getQuery from "../../helper/getQuery";

export const getCandidatesInTables = async (table_id : string, utc : Promise<string>) : Promise<any> => {
    const sql = `
        SELECT u.id, u.name, CONVERT_TZ(ur.created_at, '-00:00', ?) as request_date, u.discord_username as discord
            FROM Users_registration ur 
                JOIN Users u ON u.id = ur.user_id 
                WHERE ur.table_id = ?
                    AND ur.status = 'Candidate'
                    AND ur.status != 'Deleted'
                    AND u.status = 'Allowed'
                ORDER BY request_date DESC`;

    const params = [utc, table_id];
    const response = await getQuery(sql, params);
    
    return response;
}