import { Request, Response } from "express";
import { Connection, ResultSetHeader } from "mysql2/promise";
import { getUserTimezone } from "../helper/getUserTimezon";
import { TablesIndex } from "../models/models";
import { getPublicTable } from "../helper/getPublicTables";
import { getJoinedTable } from "../helper/getJoinedTables";

//TODO: Check the query which it doesnt work in change the hour.
//TODO: Make the trigger!!!

export function getTablesIndex (conn : Connection) {
    return async (req : Request, res : Response) => {
        try {
            const data : TablesIndex = {
                public_tables: null,
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

            data.public_tables = await getPublicTable(conn, utc).then((data) => {
                if(data.http_status) {
                    throw data;
                }

                return data;
            }).catch((err) => {
                if(err.http_status)
                    throw err;
                else
                    throw {...err, http_status: 503}
            })

            data.joined_tables = await getJoinedTable(conn, utc, user_id).then((data) => {
                if(data.http_status) {
                    throw data;
                }

                return data;
            }).catch((err) => {
                if(err.http_status)
                    throw err;
                else
                    throw {...err, http_status: 503}
            })

            res.status(200).send(data);
        }
        catch (err : any) {
            res.status(err.http_status ? err.http_status : 500).send({...err, http_status: undefined})
        }
    }
}