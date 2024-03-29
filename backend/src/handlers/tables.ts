import { Request, Response } from "express";
import { Connection, ResultSetHeader } from "mysql2/promise";
import { getUserTimezone } from "../helper/getUserTimezon";
import { TablesIndex } from "../models/models";

//TODO: Check the query which it doesnt work in change the hour.
//TODO: Make the trigger!!!

export function getTablesIndex (conn : Connection) {
    const sql_public = `
        SELECT t.id, t.name, t.description, CONVERT_TZ(t.startdate, t.timezone, ?) as startdate, GROUP_CONCAT(u.name) as master
            FROM Tables t 
            JOIN Masters m ON m.table_id = t.id 
            JOIN Users u ON m.user_master_id = u.id
        WHERE t.status = 'Opened'
            AND u.status = 'Allowed'
            AND t.table_type = 'Public'
            AND m.status = 'Created'
        GROUP BY t.id 
        ORDER BY startdate 
        LIMIT 6`;
    const sql_first_class = `
    SELECT t.id, t.name, t.description, CONVERT_TZ(t.startdate, t.timezone, ?) as startdate, GROUP_CONCAT(u.name) as master
        FROM Tables t 
        JOIN Masters m ON m.table_id = t.id 
        JOIN Users u ON m.user_master_id = u.id
        WHERE t.status = 'Opened'
            AND u.status = 'Allowed'
            AND t.table_type = 'First class'
            AND m.status = 'Created'
        GROUP BY t.id 
        ORDER BY startdate 
        LIMIT 6`;

        const joined_tables = `
        SELECT t.id, t.name, t.description, CONVERT_TZ(t.startdate, t.timezone, ?), GROUP_CONCAT(u.name) as master
            FROM Tables t 
            JOIN Masters m ON m.table_id = t.id 
            JOIN Users u ON m.user_master_id = u.id
            WHERE t.status = 'Opened'
                AND u.status = 'Allowed'
                AND t.type = 'First_class'
            GROUP BY t.id 
            ORDER BY startdate 
            LIMIT 6`;

    return async (req : Request, res : Response) => {
        try {
            const data : TablesIndex = {
                public_tables: null,
                first_class_tables: null,
                joined_tables: null
            }

            const user_id = req.params.user_id;

            const utc : Promise<string> = await getUserTimezone(conn, user_id).then((utc) => {
                if(utc.http_status) {
                    throw utc;
                }

                return utc[0].timezone;
            }).catch((err) => {
                if(err.http_status)
                    throw err;
                else
                    throw {...err, http_status: 503}
            })

            await conn.execute(sql_public, [utc]).then(([rows, fields]) => {
                data.public_tables = rows;
            }).catch((err) => {
                throw {...err, http_status: 503}
            });

            await conn.execute(sql_first_class, [utc]).then(([rows, fields]) => {
                data.first_class_tables = rows;
            }).catch((err) => {
                throw {...err, http_status: 503}
            });

            res.status(200).send(data);
        }
        catch (err : any) {
            res.status(err.http_status ? err.http_status : 500).send({...err, http_status: undefined})
        }
    }
}