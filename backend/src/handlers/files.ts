import { Request, Response } from "express";
import { PoolConnection } from "mysql2/promise";
import { conn } from "../config/database";
import { setFilesByMaster } from "../server/files/setFilesByMaster";
import { setFilesToTables } from "../server/files/setFilesToTables";
import deleteFile from "../helper/deleteFile";

function isObjectWithFieldnames(files: { [fieldname: string]: Express.Multer.File[]; } | Express.Multer.File[]): files is { [fieldname: string]: Express.Multer.File[]; } {
    return typeof files === 'object' && files !== null && !Array.isArray(files);
}

export function handleUploadFiles () {
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

            if(files) {
                if(!isObjectWithFieldnames(files)) {
                    for(const file of files) {
                        const file_id = await setFilesByMaster(user_id, file, query).then((response) => {
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
                    }
                }
            }
            
            await query.commit();
            await query.release();
            
            res.status(200).send('Uploaded successfully');
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

                    return res.status(418).send({...err, http_status: undefined})
                }
            }
            
            return res.status(err.http_status ? err.http_status : 500).send({...err, http_status: undefined })
        }
    }
}