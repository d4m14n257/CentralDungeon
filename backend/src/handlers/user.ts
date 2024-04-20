import { Request, Response } from "express";
import { getUsersMaster } from "../server/users/getUserMasters";
import { MastersList } from "../models/models";

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