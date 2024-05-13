import express, { Router } from "express";
import { uploadFiles } from "../middleware/uploadFiles";
import { handleDeleteFilesByTable, handleFilesFromTables, handleUploadFilesByMasters } from "../handlers/files";

export const files : Router = express.Router();

files.get('/tables/preparation/:table_id/:user_id', handleFilesFromTables());

files.post('/tables/preparation/:table_id/:user_id', uploadFiles, handleUploadFilesByMasters());

// files.put('/tables/preparation/:table_id/:user_id' );

files.delete('/tables/preparation/:table_id/:file_id', handleDeleteFilesByTable())
