import { Request, Response } from "express";
import { PoolConnection } from "mysql2/promise";
import { conn } from "../config/database";
import { setFilesByMaster } from "../server/files/setFilesByMaster";
import { setFilesToTables } from "../server/files/setFilesToTables";
import deleteFile from "../helper/deleteFile";
import { deleteFileByTables } from "../server/files/deleteFileByTables";
import { getPublicFileFromTable } from "../server/files/getPublicFilesFromTable";
import { getAllFilesByMaster } from "../server/files/getAllFilesByMaster";
import { FileList } from "../models/models";
import { updateFileStatus } from "../server/files/updateFileStatus";

function isObjectWithFieldnames(files: { [fieldname: string]: Express.Multer.File[]; } | Express.Multer.File[]): files is { [fieldname: string]: Express.Multer.File[]; } {
    return typeof files === 'object' && files !== null && !Array.isArray(files);
}

export function handleFilesFromTables () {
    return async (req : Request, res: Response) => {
        try {
            const data = {
                public_files: null,
                private_files: null
            }

            const table_id = req.params.table_id;
            const user_id = req.params.user_id;

            data.public_files = await getPublicFileFromTable().then((response) => {
                if(response.http_status)
                    throw response;

                return response
            })
            .catch((err) => {
                throw err;
            });

            data.private_files = await getAllFilesByMaster(user_id, table_id).then((response) => {
                if(response.http_status)
                    throw response;

                return response;
            })
            .catch((err) => {
                throw err;
            })

            res.status(200).send(data)
        }
        catch (err : any) {
            res.status(err.http_status ? err.http_status : 500).send({...err, http_status: undefined })
        }
    }
}

export function handleCreateFilesByMasters () {
    //TODO: This must be to use by every status on tables.
    let type_file = 'Single-use';

    return async (req : Request, res : Response) => {
        try {
            const query : PoolConnection = await conn.getConnection()
            .catch((err) => {
                throw {...err, http_status: 503}
            })

            await query.beginTransaction();

            const user_id = req.params.user_id;
            const table_id = req.params.table_id;
            const files = req.files
            const files_created = [];

            if(files) {
                if(!isObjectWithFieldnames(files)) {
                    for(const file of files) {
                        const file_id = await setFilesByMaster(user_id, file, type_file, query).then((response) => {
                            if(response.http_status != 200)
                                throw response

                            return response.id
                        })
                        .catch((err) => {
                            query.rollback();
                            query.release();
                            
                            throw {...err, files: files}
                        })

                        await setFilesToTables(file_id, table_id, query).then((response) => {
                            if(response.http_status != 200)
                                throw response
                        })
                        .catch((err) => {
                            query.rollback();
                            query.release();
                            
                            throw {...err, files: files}
                        })

                        files_created.push({id: file_id, path: file.path})
                    }
                }
            }
            
            await query.commit();
            await query.release();
            
            res.status(201).send(files_created);
        }
        catch (err : any) {
            if(err.files) {
                if(!isObjectWithFieldnames(err.files)) {
                    for(const file of err.files) {
                        await deleteFile(file).then((response) => {
                            if(response) {
                                throw {status: response.status, message: response.message}
                            }
                        })
                        .catch((err) => {
                            res.status(err.status).send(err.message);
                        });
                    }

                    return res.status(418).send({...err, http_status: undefined, files: undefined})
                }
            }
            
            return res.status(err.http_status ? err.http_status : 500).send({...err, http_status: undefined });
        }
    }
}

export function handleUpdateFilesByMasters () {
    //TODO: Create a queue when the connection fall must delete all new files.
    //TDDO: Type files
    return async (req : Request, res : Response) => {
        try {
            const query : PoolConnection = await conn.getConnection()
            .catch((err) => {
                throw {...err, http_status: 503}
            })

            await query.beginTransaction();

            const table_id = req.params.table_id;

            const files_list : FileList[] = req.body.files_list;
            const original_files : FileList[] = req.body.original_files;
            const update_files : FileList[] = req.body.update_files;
            const files : FileList[] = req.body.files;

            files_list.map((item) => {
                for(let i = 0; i < original_files.length; i++) {
                    if(original_files[i].id == item.id) {
                        original_files.splice(i, 1);

                        break;
                    }
                }
            })

            const toDelete = original_files;

            if(toDelete.length > 0) {
                for(const file of toDelete) {
                    await deleteFileByTables(file.id, table_id, query).then((response) => {
                        if(response.http_status != 200)
                            throw response;
                    })
                    .catch((err) => {
                        query.rollback();
                        query.release();

                        throw {...err, files: files};
                    })
                }
            }

            if(update_files.length > 0) {
                for(const file of update_files) {
                    await setFilesToTables(file.id, table_id, query).then((response) => {
                        if(response.http_status != 200)
                            throw response;
                    })
                    .catch((err) => {
                        query.rollback();
                        query.release();

                        throw {...err, files: files};
                    })
                }
            }

            await query.commit();
            await query.release();
            
            res.status(201).send({message: 'Succefully upload files'});
        }
        catch (err : any) {
            if(err.files) {
                for(const file of err.files) {
                    await deleteFile(file).then((response) => {
                        if(response) {
                            throw {status: response.status, message: response.message}
                        }
                    })
                    .catch((err) => {
                        res.status(err.status).send(err.message);
                    });
                }

                return res.status(418).send({...err, http_status: undefined, files: undefined})
            }
            
            return res.status(err.http_status ? err.http_status : 500).send({...err, http_status: undefined });
        }
    }
}

export function handleDeleteFilesByTable () {
    return async (req : Request, res: Response) => {
        try {
            const query : PoolConnection = await conn.getConnection()
            .catch((err) => {
                throw {...err, http_status: 503}
            })

            await query.beginTransaction();

            const file_id = req.params.file_id;
            const table_id = req.params.table_id;

            await deleteFileByTables(file_id, table_id, query).then((response) => {
                if(response.http_status != 200)
                    throw response
            })
            .catch((err) => {
                query.rollback();
                query.release();
                
                throw err;
            })

            await query.commit();
            await query.release();

            res.status(200).send({ message: 'Succefully deleted file'})

        }
        catch (err : any) {
            return res.status(err.http_status ? err.http_status : 500).send({...err, http_status: undefined })
        }
    }
}

export function handlePrivateStatus() {
    //TODO: Keep in queue if it get a error.
    const status = 'Private';

    return async (req : Request, res: Response) => {
        try {
            const query : PoolConnection = await conn.getConnection()
            .catch((err) => {
                throw {...err, http_status: 503}
            })

            await query.beginTransaction();
            const files : FileList[] = req.body;

            if(files.length > 0) {
                for(const file of files) {
                    await updateFileStatus(file.id, status, query).then((response) => {
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

            await query.commit();
            await query.release();

            res.status(200).send({ message: 'Succefully save file'})
        }
        catch (err : any){
            return res.status(err.http_status ? err.http_status : 500).send({...err, http_status: undefined })
        }
    }
}