import { conn } from "../config/database";
import { Request, Response } from "express";
import { PoolConnection } from "mysql2/promise";
import { GeneralMasterView, Generalview, Table, TableMasterList } from "../models/models";
import { getUserTimezone } from "../helper/getUserTimezon";
import { getPublicTables } from "../server/tables/getPublicTables";
import { getJoinedTables } from "../server/tables/getJoinedTables";
import { getRequestToTables } from "../server/tables/getRequestToTable";
import { getOwnerTables } from "../server/tables/getOwnerTables";
import { getMasterTables } from "../server/tables/getMasterTables";
import { getRequestOnTables } from "../server/tables/getRequestOnTable";
import { getFirstClassTables } from "../server/tables/getFirstClassTables";
import { setTable } from "../server/tables/setTable";
import { setCatalogue } from "../server/catalogues/setCatalogues";
import { setCatalogueTables } from "../server/catalogues/setCataloguesTable";
import { setScheduleTables } from "../server/schedule/setScheduleTable";
import { setTableMasters } from "../server/users/setTableMasters";
import { getTablesLists } from "../server/tables/getTableLists";
import { deleteTables } from "../server/tables/deleteTables";
import { deleteDaysByTables } from "../server/schedule/deleteDaysByTables";
import { deleteCataloguesByTables } from "../server/catalogues/deleteCataloguesByTables";
import { deleteUsersMastersByTables } from "../server/tables/deleteUsersMastersByTables";
import { deleteUserByTables } from "../server/users/deleteUsersByTables";
import { getTable } from "../server/tables/getTable";
import { getCataloguesInTables } from "../server/catalogues/getCataloguesInTables";
import { deleteFilesByTables } from "../server/files/deleteFilesByTables";
import { deleteMastersByTables } from "../server/users/deleteMastersByTables";
import { getMastersInTables } from "../server/users/getMastersInTables";
import { getScheduleInTables } from "../server/schedule/getScheduleInTables";
import { getPlayersInTables } from "../server/users/getPlayersInTables";
import { updateTable } from "../server/tables/updateTable";
import { deleteDayScheduleByTables } from "../server/schedule/deleteDayScheduleByTables";
import { getFilesByTables } from "../server/files/getFilesByTables";

//TODO: Check the query which it doesnt work in change the hour.
//TODO: When ill save a new candidate while its uploading, save the date in the web client, no serve o another.

export function handleGetAllTable () {
    return async (req: Request, res : Response) => {
        try {
            const data : { 
                table: Table | null
            } = {
                table: null
            }

            const table_id = req.params.table_id;

            data.table = await getTable(table_id).then((data) => {
                if(data.http_status)
                    throw data;

                return data[0];
            })
            .catch((err) => {
                if(err.http_status)
                    throw err;
                else
                    throw {...err, http_status: 503}
            });

            if(data.table) {
                const tags = await getCataloguesInTables('Tags', table_id).then((data) => {
                    if(data.http_status)
                        throw data;
                    
                    return data
                })
                .catch((err) => {
                    if(err.http_status)
                        throw err;
                    else
                        throw {...err, http_status: 503}
                });

                const systems = await getCataloguesInTables('Systems', table_id).then((data) => {
                    if(data.http_status)
                        throw data;
                    
                    return data
                })
                .catch((err) => {
                    if(err.http_status)
                        throw err;
                    else
                        throw {...err, http_status: 503}
                });

                const platforms = await getCataloguesInTables('Platforms', table_id).then((data) => {
                    if(data.http_status)
                        throw data;
                    
                    return data
                })
                .catch((err) => {
                    if(err.http_status)
                        throw err;
                    else
                        throw {...err, http_status: 503}
                });

                const masters = await getMastersInTables(table_id).then((data) => {
                    if(data.http_status)
                        throw data;
                    
                    return data
                })
                .catch((err) => {
                    if(err.http_status)
                        throw err;
                    else
                        throw {...err, http_status: 503}
                })

                const schedule = await getScheduleInTables(table_id).then((data) => {
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

                const players = await getPlayersInTables(table_id).then((data) => {
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

                const files = await getFilesByTables(table_id).then((data) => {
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

                data.table = {
                    ...data.table, 
                    tags: tags, 
                    systems: systems, 
                    platforms: platforms, 
                    masters: masters, 
                    schedule: schedule,
                    players: players,
                    files: files
                }
            }
            else {
                return res.status(203).send({ message: 'Data does not exists' })
            }
            
            return res.status(200).send(data.table);
        }
        catch (err : any) {
            res.status(err.http_status ? err.http_status : 500).send({...err, http_status: undefined})
        }
    }
}

export function handleGetGeneralView () {
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

            data.public_tables = await getPublicTables(utc, user_id).then((data) => {
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

            data.joined_tables = await getJoinedTables(utc, user_id).then((data) => {
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

export function handleGetMasterView () {
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

export function handleGetPublicTables () {
    return async (req : Request, res : Response) => {
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

            data.public_tables = await getPublicTables(utc, user_id).then((data) => {
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

export function handleGetFirstClassTables () {
    return async (req : Request, res : Response) => {
        try {
            const data = {
                first_class_tables: null
            }

            const user_id = req.params.user_id;
            const utc : Promise<string> = await getUserTimezone(user_id).then((utc) => {
                if(utc.http_status) {
                    throw utc;
                }

                return utc[0].timezone;
            }).catch((err) => {
                throw err;
            })

            data.first_class_tables = await getFirstClassTables(utc, user_id).then((data) => {
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

export function handleGetJoinedTables () {
    return async (req : Request, res : Response) => {
        try {
            const data = {
                joined_tables: null
            }

            const user_id = req.params.user_id;
            const utc : Promise<string> = await getUserTimezone(user_id).then((utc) => {
                if(utc.http_status) {
                    throw utc;
                }

                return utc[0].timezone;
            }).catch((err) => {
                throw err;
            })

            data.joined_tables = await getJoinedTables(utc, user_id).then((data) => {
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

export function handleGetTablesMasterList () {
    return async (req : Request, res: Response) => {
        try {
            const data : TableMasterList = {
                table_list : null
            }

            const user_id = req.params.user_id;
            data.table_list = await getTablesLists(user_id).then((data) => {
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

export function handleCreateTable () {
    return async (req : Request, res : Response) => {
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

                    await setCatalogueTables(table_id, tag_id, 'Tags', query).then((response) => {
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

                    await setCatalogueTables(table_id, system_id, 'Systems', query).then((response) => {
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

                    await setCatalogueTables(table_id, platform_id, 'Platforms', query).then((response) => {
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
                        await setScheduleTables(table_id, {weekday: schedule.day, hourtime: hour}, query).then((response) => {
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

            res.status(201).send({ message: 'Succefully created table'})
        }
        catch (err : any) {
            res.status(err.http_status ? err.http_status : 500).send({...err, http_status: undefined })
        }
    }
}

export function handleUpdateTable () {
    return async (req : Request, res : Response) => {
        try {
            const query : PoolConnection = await conn.getConnection()
            .catch((err) => {
                throw {...err, http_status: 503}
            })

            await query.beginTransaction();
            const body : Table = req.body;

            await updateTable(body, query).then((response) => {
                if(response.http_status != 200)
                    throw response

            }).catch((err) => {
                query.rollback();
                query.release();

                throw err;
            })

            await query.commit();
            await query.release();

            res.status(200).send({ message: 'Succefully updated table'});
        }
        catch (err : any) {
            res.status(err.http_status ? err.http_status : 500).send({...err, http_status: undefined })
        }
    }
}

export function handleUpdateScheduleTable () {
    return async (req : Request, res : Response) => {
        try {
            const table_id = req.params.table_id;

            console.log(req.body)

            res.status(200).send({message: 'Successfully update schedule'})
        }
        catch (err : any) {
            res.status(err.http_status ? err.http_status : 500).send({...err, http_status: undefined })
        }
    }
}

export function handleDeleteTable () {
    return async (req : Request, res : Response, ) => {
        try {
            const query : PoolConnection = await conn.getConnection()
            .catch((err) => {
                throw {...err, http_status: 503}
            })

            await query.beginTransaction();
            const table_id = req.params.table_id;

            await deleteTables(table_id, query).then((response) => {
                if(response.http_status != 200)
                    throw response
            }).catch((err) => {
                
                query.rollback();
                query.release();
                
                throw err
            });

            await deleteDaysByTables(table_id, query).then((response) => {
                if(response.http_status != 200)
                    throw response

            }).catch((err) => {
                
                query.rollback();
                query.release();
                
                throw err
            });


            await deleteCataloguesByTables('Table_Platforms', table_id, query).then((response) => {
                if(response.http_status != 200)
                    throw response
            }).catch((err) => {
                
                query.rollback();
                query.release();
                
                throw err
            });

            await deleteCataloguesByTables('Table_Systems', table_id, query).then((response) => {
                if(response.http_status != 200)
                    throw response
            }).catch((err) => {
                
                query.rollback();
                query.release();
                
                throw err
            });

            await deleteCataloguesByTables('Table_Tags', table_id, query).then((response) => {
                if(response.http_status != 200)
                    throw response
            }).catch((err) => {
                
                query.rollback();
                query.release();
                
                throw err
            });

            await deleteUsersMastersByTables(table_id, query).then((response) => {
                if(response.http_status != 200)
                    throw response
            }).catch((err) => {
                
                query.rollback();
                query.release();
                
                throw err
            });

            await deleteUserByTables(table_id, query).then((response) => {
                if(response.http_status != 200)
                    throw response
            }).catch((err) => {
                
                query.rollback();
                query.release();
                
                throw err
            });

            await deleteFilesByTables(table_id, query).then((response) => {
                if(response.http_status != 200)
                    throw response
            }).catch((err) => {
                
                query.rollback();
                query.release();
                
                throw err
            })

            await deleteMastersByTables(table_id, query).then((response) => {
                if(response.http_status != 200)
                    throw response
            }).catch((err) => {

                query.rollback();
                query.release();
                
                throw err
            })

            await query.commit();
            await query.release();

            res.status(200).send({ message: 'Succefully deleted table'})
        }
        catch (err : any) {
            res.status(err.http_status ? err.http_status : 500).send({...err, http_status: undefined })
        }
    }
}

export function handleDeleteSchedule () {
    return async (req : Request, res : Response) => {
        try {
            const query : PoolConnection = await conn.getConnection()
            .catch((err) => {
                throw {...err, http_status: 503}
            })

            const body = req.body;
            const table_id = req.params.table_id;

            await deleteDayScheduleByTables(table_id, body, query).then((response) => {
                if(response.http_status != 200)
                    throw response
            }).catch((err) => {
                query.rollback();
                query.release();
                
                throw err
            })

            await query.commit();
            await query.release();

            res.status(200).send({ message: 'Succefully deleted schedule'})
        }
        catch (err : any) {
            res.status(err.http_status ? err.http_status : 500).send({...err, http_status: undefined })
        }
    }
}

export function handleDeleteMultipleTables () {
    return async (req : Request, res : Response) => {
        try {
            const query : PoolConnection = await conn.getConnection()
            .catch((err) => {
                throw {...err, http_status: 503}
            })

            await query.beginTransaction();
            const body = req.body;

            for(const table_id of body) {
                await deleteTables(table_id, query).then((response) => {
                    if(response.http_status != 200)
                        throw response
                }).catch((err) => {
                    
                    query.rollback();
                    query.release();
                    
                    throw err
                });
    
                await deleteDaysByTables(table_id, query).then((response) => {
                    if(response.http_status != 200)
                        throw response
    
                }).catch((err) => {
                    
                    query.rollback();
                    query.release();
                    
                    throw err
                });
    
    
                await deleteCataloguesByTables('Table_Platforms', table_id, query).then((response) => {
                    if(response.http_status != 200)
                        throw response
                }).catch((err) => {
                    
                    query.rollback();
                    query.release();
                    
                    throw err
                });
    
                await deleteCataloguesByTables('Table_Systems', table_id, query).then((response) => {
                    if(response.http_status != 200)
                        throw response
                }).catch((err) => {
                    
                    query.rollback();
                    query.release();
                    
                    throw err
                });
    
                await deleteCataloguesByTables('Table_Tags', table_id, query).then((response) => {
                    if(response.http_status != 200)
                        throw response
                }).catch((err) => {
                    
                    query.rollback();
                    query.release();
                    
                    throw err
                });
    
                await deleteUsersMastersByTables(table_id, query).then((response) => {
                    if(response.http_status != 200)
                        throw response
                }).catch((err) => {
                    
                    query.rollback();
                    query.release();
                    
                    throw err
                });
    
                await deleteUserByTables(table_id, query).then((response) => {
                    if(response.http_status != 200)
                        throw response
                }).catch((err) => {
                    
                    query.rollback();
                    query.release();
                    
                    throw err
                });
    
                await deleteFilesByTables(table_id, query).then((response) => {
                    if(response.http_status != 200)
                        throw response
                }).catch((err) => {
                    
                    query.rollback();
                    query.release();
                    
                    throw err
                })
    
                await deleteMastersByTables(table_id, query).then((response) => {
                    if(response.http_status != 200)
                        throw response
                }).catch((err) => {
    
                    query.rollback();
                    query.release();
                    
                    throw err
                })
            }

            await query.commit();
            await query.release();

            res.status(200).send({ message: 'Succefully deleted table'})
        }
        catch (err : any) {
            res.status(err.http_status ? err.http_status : 500).send({...err, http_status: undefined })
        }
    }
}