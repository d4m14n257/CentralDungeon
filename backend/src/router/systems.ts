import express, { Router } from "express";
import { handleCreateCatalogues, handleGetCatalogueIndex, handleGetCatalogues, handleSetCatalogue, handleSetCatalogueIndex } from "../handlers/catalogues";
import { Systems } from "../models/models";

export const systems : Router = express.Router();
const table_name = 'Systems';
const column_name = 'system_id'

systems.get('/:name', handleGetCatalogues(table_name));
systems.get('/:table_id/:name', handleGetCatalogues(table_name));
systems.get('/index/:index', handleGetCatalogueIndex(table_name));

systems.post('/', handleCreateCatalogues<Systems>(table_name));

systems.put('/index', handleSetCatalogueIndex<Systems>(table_name, column_name));
systems.put('/tables/:table_id', handleSetCatalogue<Systems>(table_name));