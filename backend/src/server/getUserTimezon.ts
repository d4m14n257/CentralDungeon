import { conn } from "../config/database";

export const getUserTimezone = async (user_id : string) : Promise<any> => {
    const sql = `
    SELECT timezone
        FROM Users
        WHERE id = ?`;

    try {
        const data = { timezone: {}}

        await conn.execute(sql, [user_id]).then(([rows, field]) => {
            data.timezone = rows;
        }).catch((err : any) => {
            throw {...err, http_status: 500};
        })  

        return data.timezone;
    }
    catch (err : any){
        return err;
    }
}