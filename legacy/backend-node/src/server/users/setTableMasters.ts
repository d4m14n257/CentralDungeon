import { PoolConnection } from "mysql2/promise";
import setQuery from "../../helper/setQuery";
import { Masters } from "../../models/models";

export const setTableMasters = async (table_id : string, master : Masters, query : PoolConnection) => {
    let sql = `
        INSERT INTO Masters (table_id, user_master_id`;

    if(master.master_type) 
        sql += `, master_type) VALUES (? ,?, ?)`;
    else
        sql += `) VALUES (? ,?)`;

    const params = master.master_type ? [table_id, master.id, master.master_type] : [table_id, master.id];
    const response = await setQuery(sql, params, query);

    return await {...response}

}