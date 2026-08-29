import express, { Router } from "express";
import { uploadFiles } from "../middleware/uploadFiles";
import { 
    handleDeleteFilesByTable, 
    handleFilesFromTables, 
    handleCreateFilesByMasters, 
    handleUpdateFilesByMasters,
    handlePrivateStatus 
} from "../handlers/files";

export const files : Router = express.Router();

files.get('/tables/preparation/:table_id/:user_id', handleFilesFromTables());

files.post('/tables/preparation/:table_id/:user_id', uploadFiles, handleCreateFilesByMasters());

files.put('/tables/preparation/:table_id', handleUpdateFilesByMasters());

files.patch('/private/status', handlePrivateStatus())

files.delete('/tables/preparation/:table_id/:file_id', handleDeleteFilesByTable())
