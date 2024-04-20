import { RowDataPacket } from "mysql2";
import { conn } from "../config/database";

export default async function getQuery (sql : string, params : any[] = []) {
    let data;

    try {
        const query = await conn.getConnection().catch((err) => {
            throw {...err, http_status: 503}
        })

        await query.execute<RowDataPacket[]>(sql, params).then(([rows, field]) => {
            data = rows
        }).catch((err : any) => {
            query.release()
            throw {...err, http_status: 500};
        })  
        
        query.release()
    }
    catch (err : any){
        return err;
    }
    
    return data;
}