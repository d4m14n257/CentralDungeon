import { useRef, useState } from 'react';

import CardContent from '@mui/material/CardContent';
import CardActions from '@mui/material/CardActions';
import Button from '@mui/material/Button';
import ButtonGroup from '@mui/material/ButtonGroup';

import RequerimentsModalTable from './modals/RequerimentsModalTable'; 
import RequestModalTable from './modals/RequestModalTable';
import CardContentTable from './CardContentTable';

import { card } from '@/styles/tables/card';

export default function CardBodyTable (props) {
    const { table } = props;

    const [openRequerimentsModal, setOpenRequerimentsModal] = useState(false);
    const [openRequestModal, setOpenRequestModal] = useState(false);

    const handleDownloadFiles = () => {
        table.files.map((file) => {
            const link = document.createElement('a');
            link.href = file;
            link.download = "CV-Jorge-Damian-Dominguez-Jimenez-English.pdf"; 
            link.click();
        })
    }

    const handleOpenRequerimentsModal = () => {
        setOpenRequerimentsModal(true);
    }

    const handleCloseRequerimentsModal = () => {
        setOpenRequerimentsModal(false);
    }

    const handleOpenRequestModal = () => {
        setOpenRequestModal(true);
    }

    const handleCloseRequestModal = () => {
        setOpenRequestModal(false);
    }

    return (
        <>
            <CardContent>
                <CardContentTable 
                    table={table}
                />
            </CardContent>
            <CardActions sx={card.cardActions}>
                <ButtonGroup>
                    <Button onClick={handleOpenRequerimentsModal}>Requisitos</Button>
                    <Button onClick={handleOpenRequestModal}>Solicitar</Button>
                    <Button onClick={handleDownloadFiles}>Descargar</Button>
                </ButtonGroup>
            </CardActions>
            <RequerimentsModalTable 
                isOpen={openRequerimentsModal}
                handleCloseModal={handleCloseRequerimentsModal}
                handleOpenRequestModal={handleOpenRequestModal}
                handleDownloadFiles={handleDownloadFiles}
                requeriments={table.requeriments}
            />
            {openRequestModal && 
                <RequestModalTable 
                    isOpen={openRequestModal}
                    handleCloseModal={handleCloseRequestModal}
                />
            }
        </>
    );
}