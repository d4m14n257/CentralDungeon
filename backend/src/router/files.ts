import express, { Router } from "express";
import { uploadFiles } from "../middleware/uploadFiles";
import { handleDeleteFilesByTable, handleUploadFilesByMasters } from "../handlers/files";

export const files : Router = express.Router();

files.post('/tables/preparation/:table_id/:user_id', uploadFiles, handleUploadFilesByMasters());

files.delete('/tables/preparation/:table_id/:file_id', handleDeleteFilesByTable())
