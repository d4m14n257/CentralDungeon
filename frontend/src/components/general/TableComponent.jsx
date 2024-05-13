import { useState, useReducer, useMemo, useContext, useEffect } from 'react';

import { Paper, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, TableCellUsers, TableSortLabel, Toolbar, Typography,
         Box, Checkbox, Tooltip, IconButton } from '@mui/material';

import { alpha } from '@mui/material/styles';

import DeleteIcon from '@mui/icons-material/Delete';
import FilterListIcon from '@mui/icons-material/FilterList';
import CancelIcon from '@mui/icons-material/Cancel';

import { global } from '@/styles/global';
import { Confirm } from '@/contexts/ConfirmContext';
import { Message } from '@/contexts/MessageContext';
import { deleter } from '@/api/deleter';

function stableSort(array, comparator) {
    const stabilizedThis = array.map((el, index) => [el, index]);

    stabilizedThis.sort((a, b) => {
        const order = comparator(a[0], b[0]);
        if (order !== 0) {
            return order;
        }
        return a[1] - b[1];
    });

    return stabilizedThis.map((el) => el[0]);
}

function descendingComparator(a, b, orderBy) {
    if (b[orderBy] < a[orderBy]) {
        return -1;
    }
    if (b[orderBy] > a[orderBy]) {
        return 1;
    }
    return 0;
}

function getComparator(order, orderBy) {
    return order === 'desc'
        ? (a, b) => descendingComparator(a, b, orderBy)
        : (a, b) => -descendingComparator(a, b, orderBy);
}

const EnhancedTableToolbar = (props) => {
    const { title, numSelected, handleCheckboxShowUp, handleCheckboxGoAway, handleDeleteSelect, useCheckbox, checkbox } = props;

    return (
        <Toolbar
            sx={{
                pl: { sm: 2 },
                pr: { xs: 1, sm: 1 },
                ...(numSelected > 0 && {
                    bgcolor: (theme) =>
                    alpha(theme.palette.primary.main, theme.palette.action.activatedOpacity),
                }),
            }}
        >
            {numSelected > 0 ? (
                <Typography
                    sx={{ flex: '1 1 100%' }}
                    color="inherit"
                    variant="subtitle1"
                    component="div"
                >
                    {numSelected} Seleccionados
                </Typography>
                ) : (
                <Typography
                    sx={{ flex: '1 1 100%' }}
                    variant="h5"
                    id="tableTitle"
                    component="div"
                >
                    {title}
                </Typography>
            )}

            {useCheckbox &&
                <>
                    {numSelected > 0 &&
                        <Tooltip title="Delete">
                            <IconButton onClick={(event) => handleDeleteSelect(event)}>
                                <DeleteIcon />
                            </IconButton>
                        </Tooltip>
                    }
                    {checkbox ?
                        <Tooltip title="Cancelar">
                        <IconButton onClick={() => handleCheckboxGoAway()}>
                            <CancelIcon />
                        </IconButton>
                    </Tooltip> :
                        <Tooltip title="Multiselección">
                            <IconButton onClick={() => handleCheckboxShowUp()}>
                                <FilterListIcon />
                            </IconButton>
                        </Tooltip>
                    }
                </>
            }
        </Toolbar>
    );
}

const useTableComponent = ({ rows, columns, url, reloadTable }) => {
    const { confirm, setMessage } = useContext(Confirm);
    const { handleOpen, setMessage: setStatusMessage, setStatus } = useContext(Message);
    const [page, setPage] = useState(0);
    const [rowsPerPage, setRowsPerPage] = useState(10)
    const [checkboxAction, setCheckboxAction] = useState(false);
    const [order, setOrder] = useState('asc');
    const [orderBy, setOrderBy] = useState(columns[0].id);
    const [selected, setSelected] = useState([]);

    useEffect(() => {
        setMessage('¿Seguro quieres eliminar todos estos datos?');
    }, [])

    const handleDeleteSelect = async (event) => {
        if(!event.shiftKey) {
            await confirm()
                .catch(() => {throw {err: 'Canceled'}});
        }

        const response = await deleter({body: selected, url: url})

        if(response.status >= 200 && response.status <= 299) {
            setStatus(response.status);
            setStatusMessage('Datos eliminados con exito.');
            handleOpen();
            
            if(reloadTable)
                await reloadTable();

            setSelected([]);
            setCheckboxAction(false);
        }
        else {
            setStatus(response.status);
            setStatusMessage('Ha habido un error al momento de eliminar.');
            handleOpen();
        }
    }

    const handleChangePage = (event, newPage) => {
        setPage(newPage);
    };

    const handleChangeRowsPerPage = (event) => {
        setRowsPerPage(parseInt(event.target.value, 10));
        setPage(0);
    };

    const handleCheckboxShowUp = () => {
        setCheckboxAction(true);
    }

    const handleCheckboxGoAway = () => {
        setSelected([]);
        setCheckboxAction(false);
    }

    const handleSelectAll = (event) => {
        if (event.target.checked) {
            const newSelected = rows.map((n) => n.id);
            setSelected(newSelected);
            return;
        }

        setSelected([]);
    }

    const createSortHandler = (property) => (event) => {
        handleRequestSort(event, property);
    };

    const handleRequestSort = (event, property) => {
        const isAsc = orderBy === property && order === 'asc';
        setOrder(isAsc ? 'desc' : 'asc');
        setOrderBy(property);
    }

    const handleClick = (event, id) => {
        const selectedIndex = selected.indexOf(id);
        let newSelected = [];

        if (selectedIndex === -1) {
            newSelected = newSelected.concat(selected, id);
        } else if (selectedIndex === 0) {
            newSelected = newSelected.concat(selected.slice(1));
        } else if (selectedIndex === selected.length - 1) {
            newSelected = newSelected.concat(selected.slice(0, -1));
        } else if (selectedIndex > 0) {
            newSelected = newSelected.concat(
                selected.slice(0, selectedIndex),
                selected.slice(selectedIndex + 1),
            );
        }

        setSelected(newSelected);
    }

    const isSelected = (id) => selected.indexOf(id) !== -1

    const visibleRows = useMemo(
        () =>
          stableSort(rows, getComparator(order, orderBy)).slice(
            page * rowsPerPage,
            page * rowsPerPage + rowsPerPage,
          ),
        [order, orderBy, page, rowsPerPage, rows],
    );

    return {
        page,
        rowsPerPage,
        checkboxAction,
        selected,
        order,
        orderBy,
        handleDeleteSelect,
        handleCheckboxShowUp,
        handleCheckboxGoAway,
        handleSelectAll,
        handleChangeRowsPerPage,
        handleChangePage,
        handleClick,
        createSortHandler,
        visibleRows,
        isSelected
    };
}

export default function TableComponent (props) {
    const { title, columns, rows, useCheckbox, minWidth, Actions, doubleClick, reloadTable, url } = props;
    const { page, rowsPerPage, checkboxAction, orderBy, selected,
            handleCheckboxShowUp, handleSelectAll, handleChangeRowsPerPage,
            handleChangePage, handleCheckboxGoAway, handleClick, visibleRows,
            isSelected, order, createSortHandler, handleDeleteSelect
        } = useTableComponent({ rows, columns, url, reloadTable });

    return (
        <Paper sx={{...global.border, width: '100%', overflow: 'hidden'}}>
            <Box sx={{margin: 2}}>
                <EnhancedTableToolbar
                    title={title}
                    handleCheckboxShowUp={handleCheckboxShowUp}
                    handleCheckboxGoAway={handleCheckboxGoAway}
                    handleDeleteSelect={handleDeleteSelect}
                    numSelected={selected.length}
                    useCheckbox={useCheckbox}
                    checkbox={checkboxAction}
                />
                <TableContainer sx={{ maxHeight: 1000 }}>
                    <Table stickyHeader aria-label="sticky table" >
                        <TableHead>
                            <TableRow
                                key='headerTable'
                            >
                                {checkboxAction &&
                                    <TableCell
                                        padding="checkbox">
                                        <Checkbox
                                            color="primary"
                                            indeterminate={selected.length > 0 && selected.length < rows.length}
                                            checked={rows.length > 0 && selected.length === rows.length}
                                            onChange={handleSelectAll}
                                            inputProps={{
                                                'aria-label': 'select all desserts',
                                            }}
                                        />
                                    </TableCell>
                                }
                                {columns.map((column) => (
                                    <>
                                        {column.id !== 'actions' ?
                                            <TableCell
                                                key={column.id}
                                                style={{ minWidth: minWidth }}
                                                sx={{textAlign: 'left' }}
                                                sortDirection={(orderBy === column.id ? order : false)}
                                            >
                                                <TableSortLabel
                                                    active={orderBy === column.id}
                                                    direction={orderBy === column.id ? order : 'asc'}
                                                    onClick={createSortHandler(column.id)}
                                                >
                                                    <Typography variant='subtitle1' sx={{fontWeight: '700'}}>
                                                        {column.label}
                                                    </Typography>
                                                </TableSortLabel>
                                            </TableCell> :
                                            !checkboxAction &&
                                                <TableCell
                                                    key={column.id}
                                                    style={{ minWidth: minWidth }}
                                                    sx={{textAlign: 'center' }}
                                                >
                                                    <Typography variant='subtitle1' sx={{fontWeight: '700'}}>
                                                        {column.label}
                                                    </Typography>
                                                </TableCell>
                                        }
                                    </> 
                                ))}
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {visibleRows.map((row, index) => {
                                const isItemSelected = isSelected(row.id);
                                const labelId = `enhanced-table-checkbox-${index}`;

                                return (
                                    <TableRow
                                        key={row.id}
                                        hover
                                        onClick={(event) => {
                                            if(checkboxAction)
                                                handleClick(event, row.id)}
                                        }
                                        onDoubleClick={() => {
                                            if(!checkboxAction)
                                                doubleClick(row.id)
                                        }}
                                        role="checkbox"
                                        tabIndex={-1}
                                        aria-checked={isItemSelected}
                                        selected={isItemSelected}
                                    >
                                        {checkboxAction &&
                                            <TableCell
                                                padding="checkbox"
                                            >
                                                <Checkbox
                                                    color="primary"
                                                    checked={isItemSelected}
                                                    inputProps={{
                                                        'aria-labelledby': labelId,
                                                    }}
                                                />
                                            </TableCell>
                                        }
                                        {columns.map((column) => {
                                            const key = column.id;

                                            if(key !== 'actions') {
                                                const value = row[column.id];

                                                if (Array.isArray(value)) {
                                                    return (
                                                        <TableCell key={key}>
                                                            {value.join(' - ')}
                                                        </TableCell>
                                                    );
                                                } else if (typeof value == 'object') {
                                                    return (
                                                        <TableCell key={key}>
                                                            <TableCellUsers
                                                                index={key}
                                                                row={value}
                                                            />
                                                        </TableCell>
                                                    );
                                                } else {
                                                    return (
                                                        <TableCell key={key}>
                                                            {value}
                                                        </TableCell>
                                                    );
                                                }
                                            }
                                            else {
                                                if(!checkboxAction)
                                                    return (
                                                        <TableCell
                                                            key={key}
                                                            sx={{textAlign: 'center'}}
                                                        >
                                                            <Actions
                                                                id={row.id}
                                                                reloadAction={reloadTable}
                                                            />
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
                    labelRowsPerPage='Filas por pagina'
                />
            </Box>
        </Paper>
    );
}