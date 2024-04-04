import { conn } from "../config/database";

export default async function getQuery (sql : string, params : any[]) {
    let data;

    try {
        await conn.execute(sql, params).then(([rows, field]) => {
            data = rows
        }).catch((err : any) => {
            throw {...err, http_status: 500};
        })  
    }
    catch (err : any){
        return err;
    }

    return data;
}