import { ResultSetHeader } from "mysql2";
import { PoolConnection } from "mysql2/promise";

export default async function setQuery (sql : string, params : (string | null | Date | number | undefined)[], query : PoolConnection) : Promise<any> {
    try {
        await query.execute<ResultSetHeader>(sql, params).catch((err : any) => {
            throw {...err, http_status: 400};
        })  

        return { http_status: 200 }
    }
    catch (err : any){
        return err;
    }
}