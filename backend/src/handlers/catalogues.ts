import { Request, Response } from "express";
import { PoolConnection, ResultSetHeader } from "mysql2/promise";
import { Platforms, Systems, Tags } from "../models/models";
import { conn } from "../config/database";
import { getCataloguesName } from "../server/catalogues/getCataloguesName";
import { setCatalogue } from "../server/catalogues/setCatalogues";
import { deleteCataloguesByIndex } from "../server/catalogues/deleteCataloguesByIndex";
import { setCatalogueTables } from "../server/catalogues/setCataloguesTable";

export function handleCreateCatalogues<T extends Tags | Systems | Platforms>(table_name : string) {
    return async (req : Request, res : Response) => {
        try {
            const query : PoolConnection = await conn.getConnection()
            .catch((err) => {
                throw {...err, http_status: 503}
            })

            await query.beginTransaction()
            const body : T = req.body;

            await setCatalogue(body, table_name, query).then((response) => {
                if(response.http_status != 200)
                    throw {...response.err, ...response.http_status}
            }).catch((err) => {
                query.rollback();
                query.release();

                throw err;
            })
            
            query.commit();
            query.release();

            res.status(200).send('Successfully');
        }
        catch(err : any) {
            await conn.rollback;
            res.status(err.http_status ? err.http_status : 409).send({...err, http_status: undefined})
        }
    }
}

export function handleGetCatalogues(table_name : string) {
    return async (req: Request, res : Response) => {
        try {
            const name : string = req.params.name;
            const table_id : string = req.params.table_id;

            await getCataloguesName(table_name, name, table_id).then((data) => {
                if(data.http_status) {
                    throw data;
                }

                res.status(200).send({catalogues: data});
            }).catch((err) => {
                if(err.http_status)
                    throw err;
                else
                    throw {...err, http_status: 503}
            })    
        }
        catch(err : any) {
            res.status(err.http_status ? err.http_status : 500).send({...err, http_status: undefined})
        }
    }
}

export function handleGetCatalogueIndex(table_name : string) {
    const sql = `SELECT id, name FROM ${table_name} WHERE id = ?`;

    return async(req: Request, res: Response) => {
        try {
            const index = req.params.index;
            await conn.execute(sql, [index]).then(([rows, field]) => {
                res.status(200).send(rows);
            }).catch((err) => {
                throw {http_status: 503, ...err}
            });
        }
        catch (err: any){
            res.status(err.http_status ? err.http_status : 500).send({...err, http_status: undefined})
        }
    }
}

export function handleSetCatalogueIndex<T extends Tags | Systems | Platforms>(table_name : string, column_name : string) {
    const sql = `UPDATE ${table_name} SET ${column_name} = ? WHERE id = ?`;

    return async(req: Request, res: Response) => {
        try {
            await conn.beginTransaction
            const body : T = req.body;

            await conn.execute<ResultSetHeader>(sql, Object.values(body)).then(
                await conn.commit
            ).catch((err) => {
                throw {http_status: 418, ...err}
            });

            res.status(200).send("Succesfully")
        }
        catch (err: any) {
            await conn.rollback
            res.status(err.http_status ? err.http_status : 500).send({...err, http_status: undefined})
        }
    }
}

export function handleSetCatalogue<T extends Tags | Systems | Platforms>(table_name : string) {
    return async (req: Request, res: Response) => {
        try {
            const query : PoolConnection = await conn.getConnection()
            .catch((err) => {
                throw {...err, http_status: 503}
            })

            const table_id = req.params.table_id;
            const body : {original: T[], change: T[]} = req.body;

            const toFilter = body.change.filter((item) => {
                let back = true;

                for(let i = 0; i < body.original.length; i++) {
                    if(body.original[i].id == item.id) {
                        back = false;
                        body.original.splice(i, 1);

                        break;
                    }
                }

                return back;
            })

            const toDelete = body.original;

            if(toDelete.length) {
                for(const catalogue of toDelete) {
                    await deleteCataloguesByIndex(table_name, catalogue.id, table_id, query).then((response) => {
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

            if(toFilter.length) {
                for(const catalogue of toFilter) {
                    if(catalogue.status) {
                        let new_id = null;

                        if(catalogue.id == catalogue.status) {
                            new_id = await setCatalogue({id: catalogue.id, name: catalogue.id}, table_name, query).then((response) => {
                                if(response.http_status != 200)
                                    throw response;

                                return response.id
                            })
                            .catch((err) => {
                                query.rollback();
                                query.release();
    
                                throw err;
                            })
                        }
                        
                        if(!new_id)
                            new_id = catalogue.id

                        await setCatalogueTables(table_id, new_id, table_name, query).then((response) => {
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

            res.status(200).send({message: 'Succesfully updated'});
        }
        catch (err: any) {
            res.status(err.http_status ? err.http_status : 500).send({...err, http_status: undefined})
        }
    }
}

export function handleDeleteCatalogueByTable<T extends Tags | Systems | Platforms>(table_name : string) {
    return async (req : Request, res: Response) => {
        try {
            const query : PoolConnection = await conn.getConnection()
            .catch((err) => {
                throw {...err, http_status: 503}
            })

            const catalogue_id = req.params.catalogue_id;
            const table_id = req.params.table_id;

            await deleteCataloguesByIndex(table_name, catalogue_id, table_id, query).then((response) => {
                if(response.http_status != 200)
                    throw response;
            })
            .catch((err) => {
                query.rollback();
                query.release();

                throw err;
            })     

            await query.commit();
            await query.release();

            res.status(200).send({message: 'Succesfully updated'});
        }
        catch (err : any) {
            res.status(err.http_status ? err.http_status : 500).send({...err, http_status: undefined})
        }
    }
}