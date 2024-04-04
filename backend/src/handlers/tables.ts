import { Request, Response } from "express";
import { Connection } from "mysql2/promise";
import { getUserTimezone } from "../server/getUserTimezon";
import { GeneralMasterView, Generalview } from "../models/models";
import { getPublicTable } from "../server/getPublicTables";
import { getJoinedTable } from "../server/getJoinedTables";
import { getRequestToTables } from "../server/getRequestToTables";
import { getOwnerTables } from "../server/getOwnerTables";
import { getMasterTables } from "../server/getMasterTables";
import { getRequestOnTables } from "../server/getRequestOnTable";
import { getFirstClassTable } from "../server/getFirstClassTables";

//TODO: Check the query which it doesnt work in change the hour.
//TODO: Make the trigger!!!
//TODO: When ill save a new candidate while its uploading, save the date in the web client, no serve o another.

export function getGeneralView () {
    return async (req : Request, res : Response) => {
        try {
            const data : Generalview = {
                public_tables: null,
                joined_tables: null,
                request_tables: null
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

            data.public_tables = await getPublicTable(utc, user_id).then((data) => {
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

            data.joined_tables = await getJoinedTable(utc, user_id).then((data) => {
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

            data.request_tables = await getRequestToTables(utc, user_id).then((data) => {
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

export function getMasterView () {
    return async (req : Request, res : Response) => {
        try {
            const data : GeneralMasterView = {
                owner_tables: null,
                master_tables: null,
                request_tables: null
            }

            const user_id = req.params.user_id
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

            data.owner_tables = await getOwnerTables(utc, user_id).then((data) => {
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

            data.master_tables = await getMasterTables(utc, user_id).then((data) => {
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

            data.request_tables = await getRequestOnTables(utc, user_id).then((data) => {
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

export function getPublicTables () {
    return async (req: Request, res: Response) => {
        try {
            const data = {
                public_tables: null
            }

            const user_id = req.params.user_id
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

            data.public_tables = await getPublicTable(utc, user_id).then((data) => {
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

export function getFirstClassTables () {
    return async (req: Request, res: Response) => {
        try {
            const data = {
                first_class_tables: null
            }

            const user_id = req.params.user_id
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

            data.first_class_tables = await getFirstClassTable(utc, user_id).then((data) => {
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