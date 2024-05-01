import getQuery from "../../helper/getQuery";

export const getPlayersInTables = async (table_id : string) : Promise<any> => {
    const sql = `
        SELECT u.id, u.name, ur.status, u.discord_username as discord
            FROM Users_registration ur 
                JOIN Users u ON u.id = ur.user_id 
                WHERE ur.table_id = ?
                    AND ur.status != 'Deleted'
                    AND u.status = 'Allowed'`;

    const params = [table_id];
    const response = await getQuery(sql, params);
    
    return response;
}