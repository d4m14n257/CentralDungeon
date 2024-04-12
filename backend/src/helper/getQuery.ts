import { conn } from "../config/database";

export default async function getQuery (sql : string, params : any[]) {
    let data;

    try {
        const query = await conn.getConnection().catch((err) => {
            throw {...err, http_status: 503}
        })

        await query.execute(sql, params).then(([rows, field]) => {
            data = rows
        }).catch((err : any) => {
            throw {...err, http_status: 500};
        })  

        query.release()
    }
    catch (err : any){
        return err;
    }
    
    return data;
}