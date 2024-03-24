import { Request, Response } from "express";
import { Connection, ResultSetHeader } from "mysql2/promise";

export function getTablesIndex (conn : Connection) {
    const sql_public = `
        SELECT t.id, t.name, t.description, t.startdate, GROUP_CONCAT(u.name) as master
            FROM Tables t 
            JOIN Masters m ON m.table_id = t.id 
            JOIN Users u ON m.user_master_id = u.id
        WHERE t.status = 'Preparation'
            AND u.status = 'Allowed'
            AND t.type = 'Public'
        GROUP BY t.id 
        ORDER BY t.startdate 
        LIMIT 6`;
    const sql_first_class = `
    SELECT t.id, t.name, t.description, t.startdate, GROUP_CONCAT(u.name) as master
        FROM Tables t 
        JOIN Masters m ON m.table_id = t.id 
        JOIN Users u ON m.user_master_id = u.id
        WHERE t.status = 'Preparation'
            AND u.status = 'Allowed'
            AND t.type = 'First_class'
        GROUP BY t.id 
        ORDER BY t.startdate 
        LIMIT 6`;

    return async (req : Request, res : Response) => {
        try {
            let public_table;
            let first_class;

            await conn.execute(sql).then(([rows, fields]) => {
                public_table = rows;
            }).catch((err) => {
                
            });
        }
        catch (err : any) {
            res.status(err.http_status ? err.http_status : 500).send(err)
        }
    }
}