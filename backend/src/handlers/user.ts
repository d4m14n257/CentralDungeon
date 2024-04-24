import { Request, Response } from "express";
import { getUsersMaster } from "../server/users/getUserMasters";
import { MastersList, RequestPlayer } from "../models/models";
import { getUserTimezone } from "../helper/getUserTimezon";
import { getRequestToTables } from "../server/tables/getRequestToTables";

export function getUsersMasters() {
    return async (req: Request, res: Response) => {
        try {
            const data : MastersList = {
                users_master: null
            }

            const username = req.params.username;

            data.users_master = await getUsersMaster(username).then((data) => {
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
        catch (err : any){
            res.status(err.http_status ? err.http_status : 500).send({...err, http_status: undefined})
        }
    }
}

export function getRequestPlayer () {
    return async (req: Request, res: Response) => {
        try {
            const data : RequestPlayer = {
                request_tables_candidate: null,
                request_tables_rejected: null
            }

            const user_id = req.params.user_id;

            const utc : Promise<string> = await getUserTimezone(user_id).then((utc) => {
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

            data.request_tables_candidate = await getRequestToTables(utc, user_id, 'Candidate').then((data) => {
                if(data.http_status)
                    throw data;

                return data;
            })
            .catch((err) => {
                if(err.http_status)
                    throw err;
                else
                    throw {...err, http_status: 503}
            })

            data.request_tables_rejected = await getRequestToTables(utc, user_id, 'Rejected').then((data) => {
                if(data.http_status)
                    throw data;

                return data;
            })
            .catch((err) => {
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