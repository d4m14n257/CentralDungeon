import { RowDataPacket } from "mysql2";
import { conn } from "../config/database";

export const getUserTimezone = async (user_id : string) : Promise<any> => {
    const sql = `
    SELECT timezone
        FROM Users
        WHERE id = ?`;

    try {
        const query = await conn.getConnection().catch((err) => {
            throw {...err, http_status: 503}
        })

        const data = { timezone: {}}

        await query.execute<RowDataPacket[]>(sql, [user_id]).then(([rows, field]) => {
            data.timezone = rows;
        }).catch((err : any) => {
            query.release();
            throw {...err, http_status: 500};
        })  

        query.release();
        return data.timezone;
    }
    catch (err : any){
        return err;
    }
}