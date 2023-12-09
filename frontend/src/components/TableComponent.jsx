import { useState, useRef } from 'react';

import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import TableCellUsers from './users/TableCellUsers';
import IconButton from '@mui/material/IconButton'

import DeleteIcon from '@mui/icons-material/Delete';
import VisibilityIcon from '@mui/icons-material/Visibility';

export default function TableComponent (props) {
    const { columns, rows, minWidth, handleOpenModal } = props;

    const [page, setPage] = useState(0);
    const [rowsPerPage, setRowsPerPage] = useState(10);

    const handleChangePage = (event, newPage) => {
        setPage(newPage);
    };

    const handleChangeRowsPerPage = (event) => {
        setRowsPerPage(+event.target.value);
        setPage(0);
    };

    return (
        <Paper sx={{ width: '100%', overflow: 'hidden' }}>
            <TableContainer sx={{ maxHeight: 440 }}>
                <Table stickyHeader aria-label="sticky table">
                    <TableHead>
                        <TableRow>
                            {columns.map((column) => (
                                <TableCell
                                    key={column.id}
                                    style={{ minWidth: minWidth }}
                                >
                                    {column.label}
                                    {column.additional &&
                                        <><br/>{column.additional}</>}
                                </TableCell>
                            ))}
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {rows.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)
                        .map((row) => {
                            return (
                                <TableRow hover role="checkbox" tabIndex={-1} key={row.id}>
                                    {columns.map((column) => {
                                        const key = column.id;

                                        if(key !== 'actions') {
                                            const value = row[column.id];

                                            return (
                                                <TableCell key={key}>
                                                    {typeof value === 'object' ? 
                                                    <TableCellUsers 
                                                        index={key}
                                                        row={value}
                                                    />
                                                    : value }
                                                </TableCell>
                                            );
                                        }
                                        else {
                                            return (
                                                <TableCell key={key}>
                                                    <IconButton aria-label="details" onClick={() => handleOpenModal(row)}>
                                                        <VisibilityIcon />
                                                    </IconButton>
                                                    <IconButton aria-label="delete">
                                                        <DeleteIcon />
                                                    </IconButton>
                                                </TableCell>
                                            );
                                        }                                        
                                    })}
                                </TableRow>
                            );
                        })}
                    </TableBody>
                </Table>
            </TableContainer>
            <TablePagination
                rowsPerPageOptions={[10, 25, 100]}
                component="div"
                count={rows.length}
                rowsPerPage={rowsPerPage}
                page={page}
                onPageChange={handleChangePage}
                onRowsPerPageChange={handleChangeRowsPerPage}
        />
        </Paper>
    );
}