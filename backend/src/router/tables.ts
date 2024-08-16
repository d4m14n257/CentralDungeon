import express, { Router } from "express";
import { 
        handleCreateTable, 
        handleDeleteSchedule, 
        handleDeleteTable, 
        handleGetAllTable, 
        handleGetFirstClassTables, 
        handleGetGeneralView, 
        handleGetJoinedTables, 
        handleGetMasterView, 
        handleGetPublicTables, 
        handleGetTablesMasterList, 
        handleUpdateTable,
        handleDeleteMultipleTables,
        handleUpdateScheduleTable
} from "../handlers/tables";

export const tables : Router = express.Router();

tables.get('/player/:user_id', handleGetGeneralView());
tables.get('/master/:user_id', handleGetMasterView());
tables.get('/public-tables/:user_id', handleGetPublicTables());
tables.get('/first-class-tables/:user_id', handleGetFirstClassTables());
tables.get('/joined-tables/:user_id', handleGetJoinedTables());
tables.get('/master/list/:user_id', handleGetTablesMasterList());
tables.get('/:table_id/:user_id', handleGetAllTable());

tables.post('/master', handleCreateTable());

tables.put('/master', handleUpdateTable());
tables.put('/schedule/:table_id', handleUpdateScheduleTable())

tables.delete('/', handleDeleteMultipleTables());
tables.delete('/:table_id',handleDeleteTable());
tables.delete('/schedule/:table_id', handleDeleteSchedule());