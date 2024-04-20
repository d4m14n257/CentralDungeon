import { Request, response, Response } from "express";
import { getUserTimezone } from "../helper/getUserTimezon";
import { GeneralMasterView, Generalview, Table, TableMasterList } from "../models/models";
import { getPublicTable } from "../server/tables/getPublicTables";
import { getJoinedTable } from "../server/tables/getJoinedTables";
import { getRequestToTables } from "../server/tables/getRequestToTables";
import { getOwnerTables } from "../server/tables/getOwnerTables";
import { getMasterTables } from "../server/tables/getMasterTables";
import { getRequestOnTables } from "../server/tables/getRequestOnTable";
import { getFirstClassTable } from "../server/tables/getFirstClassTables";
import { conn } from "../config/database";
import { setTable } from "../server/tables/setTable";
import { PoolConnection } from "mysql2/promise";
import { setCatalogue } from "../server/catalogues/setCatalogues";
import { setCatalogueTables } from "../server/catalogues/setCataloguesTable";
import { setScheduleTables } from "../server/schedule/setScheduleTable";
import { setTableMasters } from "../server/users/setTableMasters";
import { getTablesList } from "../server/tables/getTablesList";

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
                throw err;
            })

            data.owner_tables = await getOwnerTables(utc, user_id).then((data) => {
                if(data.http_status) {
                    throw data;
                }

                return data;
            }).catch((err) => {
                throw err;
            })

            data.master_tables = await getMasterTables(utc, user_id).then((data) => {
                if(data.http_status) {
                    throw data;
                }

                return data;
            }).catch((err) => {
                throw err;
            })

            data.request_tables = await getRequestOnTables(utc, user_id).then((data) => {
                if(data.http_status) {
                    throw data;
                }

                return data;
            }).catch((err) => {
                throw err;
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
                throw err;
            })

            data.public_tables = await getPublicTable(utc, user_id).then((data) => {
                if(data.http_status) {
                    throw data;
                }

                return data;
            }).catch((err) => {
                throw err;
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
                throw err;
            })

            data.first_class_tables = await getFirstClassTable(utc, user_id).then((data) => {
                if(data.http_status) {
                    throw data;
                }

                return data;
            }).catch((err) => {
                throw err;
            })

            res.status(200).send(data);
        }
        catch (err : any) {
            res.status(err.http_status ? err.http_status : 500).send({...err, http_status: undefined})
        }
    }
}

export function getTablesMasterList () {
    return async (req : Request, res: Response) => {
        try {
            const data : TableMasterList = {
                table_list : null
            }

            const user_id = req.params.user_id;
            data.table_list = await getTablesList(user_id).then((data) => {
                if(data.http_status)
                    throw data;

                return data
            })
            .catch((err) => {
                throw err;
            })

            res.status(200).send(data);
        }
        catch (err : any) {
            res.status(err.http_status ? err.http_status : 500).send({...err, http_status: undefined})
        }
    }
}

export function createTable () {
    return async (req: Request, res: Response) => {
        try {
            const query : PoolConnection = await conn.getConnection()
            .catch((err) => {
                throw {...err, http_status: 503}
            })

            await query.beginTransaction();
            const body : Table = req.body;

            const table_id = await setTable(body, query).then((response) => {
                if(response.http_status != 200)
                    throw response

                return response.id
            }).catch((err) => {
                
                query.rollback();
                query.release();
                
                throw err
            });

            if(body.masters) {
                for(const master of body.masters) {
                    await setTableMasters(table_id, master, query).then((response) => {
                        if(response.http_status != 200)
                            throw response
                    })
                    .catch((err) => {
                        query.rollback();
                        query.release();

                        throw err;
                    })
                }
            }

            if(body.tags) {
                for(const tag of body.tags) {
                    let tag_id = null;

                    if(tag.name.includes(tag.id)) {
                        tag_id = await setCatalogue({id: tag.id, name: tag.id}, 'Tags', query).then((response) => {
                            if(response.http_status != 200)
                                throw response

                            return response.id
                        })
                        .catch((err) => {
                            query.rollback();
                            query.release();

                            throw err;
                        })
                    }

                    if(!tag_id)
                        tag_id = tag.id;

                    await setCatalogueTables(table_id, tag_id, 'Table_Tags', query).then((response) => {
                        if(response.http_status != 200)
                            throw response;
                    })
                    .catch((err) => {
                        query.rollback();
                        query.release();

                        throw err;
                    })
                }
            }

            if(body.systems) { 
                for(const system of body.systems) {
                    let system_id = null;

                    if(system.name.includes(system.id)) {
                        system_id = await setCatalogue({id: system.id, name: system.id}, 'Systems', query).then((response) => {
                            if(response.http_status != 200)
                                    throw response

                            return response.id
                        })
                        .catch((err) => {
                            query.rollback();
                            query.release();

                            throw err;
                        })
                    }
                    
                    if(!system_id)
                        system_id = system.id;

                    await setCatalogueTables(table_id, system_id, 'Table_Systems', query).then((response) => {
                        if(response.http_status != 200)
                            throw response;
                    })
                    .catch((err) => {
                        query.rollback();
                        query.release();

                        throw err;
                    })   
                }
            }

            if(body.platforms) { 
                for(const platform of body.platforms) {
                    let platform_id = null;

                    if(platform.name.includes(platform.id)) {
                        platform_id = await setCatalogue({id: platform.id, name: platform.id}, 'Platforms', query).then((response) => {
                            if(response.http_status != 200)
                                    throw response

                            return response.id
                        })
                        .catch((err) => {
                            query.rollback();
                            query.release();

                            throw err;
                        })
                    }
                    
                    if(!platform_id)
                        platform_id = platform.id;

                    await setCatalogueTables(table_id, platform_id, 'Table_Platforms', query).then((response) => {
                        if(response.http_status != 200)
                            throw response;
                    })
                    .catch((err) => {
                        query.rollback();
                        query.release();

                        throw err;
                    })     
                }
            }

            if(body.schedule) {
                for(const schedule of body.schedule) {
                    for(const hour of schedule.hour) {
                        await setScheduleTables(table_id, {day: schedule.day, hour: hour}, query).then((response) => {
                            if(response.http_status != 200)
                                throw response;
                        })
                        .catch((err) => {
                            query.rollback();
                            query.release();

                            throw err;
                        })
                    }
                }
            }

            await query.commit();
            await query.release();

            res.status(200).send({ message: 'Succefully created table'})
        }
        catch (err : any) {
            console.log(err)
            res.status(err.http_status ? err.http_status : 500).send({...err, http_status: undefined })
        }
    }
}