import express, { Router } from "express";
import { handleCreateCatalogues, handleGetCatalogues } from "../handlers/catalogues";
import { Tags } from "../models/models";

export const tags : Router = express.Router();
const table_name = 'Tags';

tags.get('/:name', handleGetCatalogues(table_name));

tags.post('/', handleCreateCatalogues<Tags>('Tags'));