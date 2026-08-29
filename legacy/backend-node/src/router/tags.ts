import express, { Router } from "express";
import { 
    handleCreateCatalogues, 
    handleGetCatalogues, 
    handleDeleteCatalogueByTable, 
    handleSetCatalogue } 
from "../handlers/catalogues";
import { Tags } from "../models/models";

export const tags : Router = express.Router();
const table_name = 'Tags';

tags.get('/:name', handleGetCatalogues(table_name));
tags.get('/:table_id/:name', handleGetCatalogues(table_name));

tags.post('/', handleCreateCatalogues<Tags>(table_name));

tags.put('/tables/:table_id', handleSetCatalogue<Tags>(table_name));

tags.delete('/tables/:catalogue_id/:table_id', handleDeleteCatalogueByTable<Tags>(table_name));