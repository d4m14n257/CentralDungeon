import getQuery from "../../helper/getQuery";

export const getUsersMaster = async (username : string) => {
    const sql = `
        SELECT u.id, u.name as username, r.name as master_type
            FROM Users u 
                JOIN Users_Roles ur ON u.id = ur.user_id 
                    AND ur.rol_id = '2'
                JOIN Roles r ON r.id = ur.rol_id
            WHERE u.name LIKE ?`;
    
    const params = [`${username}%`];
    return await getQuery(sql, params);
}